package dcraft.tool.sentinel;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.aws.AWSEC2;
import dcraft.log.Logger;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.cb.CountDownCallback;
import dcraft.xml.XElement;

import java.sql.SQLException;

/*
    Manage AWS security groups for dev and public access
 */
public class AccessUtil {
    static public ListStruct getUsersV1() {
        try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
            return conn.getResults("SELECT Id, Username, SystemUserId FROM dca_user ORDER BY Username");
        }
        catch (Exception x) {
            Logger.error("SQL error: " + x);
        }

        return ListStruct.list();
    }

    static public ListStruct getUserIPRulesV1(Long userid) {
        try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
            return conn.getResults("SELECT Id, Label, IPv4Address FROM dca_user_ip_rules WHERE UserId = ? ORDER BY Label", userid);
        }
        catch (Exception x) {
            Logger.error("SQL error: " + x);
        }

        return ListStruct.list();
    }

    static public ListStruct getUserSGRulesV1(Long ruleid) {
        try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
            return conn.getResults("SELECT daa.Id AWSAccountId, daa.Title AWSAccountTitle, daa.Alias AWSAccountAlias, dasg.SecurityGroupId AWSSecurityGroupId,\n" +
                    "\t\t\t\t\t\t\t   usg.IPv4RuleId, usg.Port, uir.IPv4Address, uir.Expires RuleExpires\n" +
                    "\t\t\t\t\t\tFROM dca_user_ip_rules uir\n" +
                    "\t\t\t\t\t\t\tINNER JOIN dca_user_security_group usg ON (usg.UserRuleId = uir.Id)\n" +
                    "\t\t\t\t\t\t\tINNER JOIN dca_deployment_security_group ddsg ON (usg.SecurityGroupId = ddsg.SecurityGroupId)\n" +
                    "\t\t\t\t\t\t\tINNER JOIN dca_aws_security_group dasg ON (ddsg.SecurityGroupId = dasg.Id)\n" +
                    "\t\t\t\t\t\t\tINNER JOIN dca_aws_account daa ON (dasg.AccountId = daa.Id)\n" +
                    "\t\t\t\t\t\tWHERE uir.Id = ?\n" +
                    "\t\t\t\t\t\tORDER BY daa.Id, dasg.Id", ruleid);
        }
        catch (Exception x) {
            Logger.error("SQL error: " + x);
        }

        return ListStruct.list();
    }

    static public void updateUserAccessV1(String ipv4, Long ruleid, String description, OperationOutcomeEmpty callback) throws OperatingContextException {
        try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
            String cidr = ipv4 + "/32";

            SqlWriter updaterule = SqlWriter.update("dca_user_ip_rules", ruleid)
                    .with("IPv4Address", cidr);

            conn.executeWrite(updaterule);

            // find and update security groups

            ListStruct sgrules = AccessUtil.getUserSGRulesV1(ruleid);

            final CountDownCallback cdcallback = new CountDownCallback(sgrules.size(), callback);

            for (int i = 0; i < sgrules.size(); i++) {
                RecordStruct sgrule = sgrules.getItemAsRecord(i);

                String label = sgrule.getFieldAsString("AWSAccountTitle");

                Logger.info("Updating " + label + " - " + sgrule.getFieldAsString("AWSSecurityGroupId") + " - " + sgrule.getFieldAsString("IPv4RuleId"));

                String alias = sgrule.getFieldAsString("AWSAccountAlias");

                AccessUtil.updateSGRuleV1(alias, label, description, cidr,
                        sgrule.getFieldAsString("AWSSecurityGroupId"), sgrule.getFieldAsString("IPv4RuleId"),
                        new OperationOutcomeEmpty() {
                            @Override
                            public void callback() throws OperatingContextException {
                                cdcallback.countDown();
                            }
                        });
            }
        }
        catch (Exception x) {
            Logger.error("SQL error: " + x);
        }
    }

    static public void updateSGRuleV1(String alias, String label, String description, String cidr, String sggroupid, String sgruleid, OperationOutcomeEmpty callback)
        throws OperatingContextException
    {
        XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws-" + alias);

        if (settings == null) {
            Logger.error("Missing settings Interchange-Aws for: " + label);
            return;
        }

        String region = settings.getAttribute("ComputeRegion");            // compute region

        ListStruct updates = ListStruct.list(RecordStruct.record()
                .with("SecurityGroupRuleId", sgruleid)
                .with("SecurityGroupRule", RecordStruct.record()
                        .with("CidrIpv4", cidr)
                        .with("IpProtocol", "tcp")
                        .with("FromPort", 22)
                        .with("ToPort", 22)
                        .with("Description", description)
                )
        );

        AWSEC2.updateSecurityGroupRuleIngress(settings, region, sggroupid, updates, new OperationOutcome<XElement>() {
            @Override
            public void callback(XElement result) throws OperatingContextException {
                if (this.hasErrors())
                    Logger.error("failed: " + alias);
                else
                    Logger.info("success: " + alias);

                callback.returnEmpty();
            }
        });
    }

    static public void lookupSGRuleV1(String alias, String label, String sggroupid, String sgruleid, OperationOutcomeRecord callback) throws OperatingContextException {
        XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws-" + alias);

        if (settings == null) {
            Logger.error("Missing settings Interchange-Aws for: " + label);
            callback.returnEmpty();
            return;
        }

        String region = settings.getAttribute("ComputeRegion");			// compute region

        AWSEC2.describeSecurityGroupRule(settings, region, sggroupid, sgruleid, new OperationOutcome<XElement>() {
            @Override
            public void callback(XElement result) throws OperatingContextException {
                if (this.hasErrors())
                    System.out.println("failed: " + alias);
                else {
                    XElement item = result.selectFirst("securityGroupRuleSet/item");

                    callback.returnValue(RecordStruct.record()
                            .with("Description", item.selectFirstText("description"))
                            .with("FromPort", item.selectFirstText("fromPort"))
                            .with("CidrIpv4", item.selectFirstText("cidrIpv4"))
                    );
                }
            }
        });
    }

    // TODO this is what is needed
    static public ListStruct accessPlan() {
        /*
            AWS
                SecGroup
                    RuleId + Port       -- support so that rule id can be missing  (rules needed)
                        Address

         */

        return null;
    }

    // TODO this is what we have on record
    // can be subtracted from the plan above or used in a verification system in periodic checks
    static public ListStruct accessRecord() {
        /*
            AWS
                SecGroup
                    RuleId + Port       -- support so that rule id can be mssing
                        Address


        SELECT daa.Id AWSAccountId, daa.Title AWSAccountTitle, daa.Alias AWSAccountAlias, dasg.SecurityGroupId AWSSecurityGroupId,
       usg.IPv4RuleId, usg.Port, uir.IPv4Address, uir.Expires RuleExpires
FROM dca_user_ip_rules uir
    INNER JOIN dca_user_security_group usg ON (usg.UserRuleId = uir.Id)
    INNER JOIN dca_deployment_security_group ddsg ON (usg.SecurityGroupId = ddsg.SecurityGroupId)
    INNER JOIN dca_aws_security_group dasg ON (ddsg.SecurityGroupId = dasg.Id)
    INNER JOIN dca_aws_account daa ON (dasg.AccountId = daa.Id)
WHERE uir.UserId = 1
ORDER BY daa.Id, dasg.Id;


         */


        return null;
    }

    // TODO take a plan and verify it is set in DB, mark/add entries as missing if so (add only to the plan, not to db)
    static public void verifyRecord() {

    }

    // TODO take a plan and verify it is set in AWS, mark/add entries as missing if so (add only to the plan, not to AWS)
    static public void verifyAccess() {

    }

    // TODO take a list and update any missing access
    static public void updateAccess() {
        /*
            AWS
                SecGroup
                    RuleId + Port       -- if missing rule id then add to aws and save in db
                        Address

         */

    }
}
